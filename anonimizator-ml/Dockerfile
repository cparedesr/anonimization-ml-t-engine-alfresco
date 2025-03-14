FROM python:3.9

RUN apt-get update && apt-get install -y \
    iptables \
    curl \
    iputils-ping

COPY . /app
WORKDIR /app

RUN pip install -r requirements.txt &&  pip install python-multipart
RUN python -m spacy download es_core_news_lg

EXPOSE 8000

CMD ["python", "service-anonimizar.py"]